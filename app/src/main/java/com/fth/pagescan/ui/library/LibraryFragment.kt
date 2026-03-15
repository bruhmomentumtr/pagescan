package com.fth.pagescan.ui.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.fth.pagescan.R
import com.fth.pagescan.databinding.FragmentLibraryBinding

class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // RecyclerView StaggeredGridLayoutManager ayarı
        binding.recyclerView.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)

        // FAB Tıklaması ile Kamera Ekranına geçiş
        binding.fabAdd.setOnClickListener {
            findNavController().navigate(R.id.action_libraryFragment_to_cameraFragment)
        }

        // List scroll olduğunda FAB'i küçültme animasyonu (Material You Best Practice)
        binding.recyclerView.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                if (dy > 0 && binding.fabAdd.isExtended) {
                    binding.fabAdd.shrink()
                } else if (dy < 0 && !binding.fabAdd.isExtended) {
                    binding.fabAdd.extend()
                }
            }
        })
        
        // Şimdilik tasarım testi için liste boş (ileride Room verisi eklenecek)
        binding.emptyStateLayout.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
